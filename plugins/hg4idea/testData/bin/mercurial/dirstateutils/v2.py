# v2.py - Pure-Python implementation of the dirstate-v2 file format
#
# Copyright Mercurial Contributors
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import struct

from ..thirdparty import attr
from .. import error, policy

parsers = policy.importmod('parsers')


# Must match the constant of the same name in
# `rust/hg-core/src/dirstate_tree/on_disk.rs`
TREE_METADATA_SIZE = 44
NODE_SIZE = 44


# Must match the `TreeMetadata` Rust struct in
# `rust/hg-core/src/dirstate_tree/on_disk.rs`. See doc-comments there.
#
# * 4 bytes: start offset of root nodes
# * 4 bytes: number of root nodes
# * 4 bytes: total number of nodes in the tree that have an entry
# * 4 bytes: total number of nodes in the tree that have a copy source
# * 4 bytes: number of bytes in the data file that are not used anymore
# * 4 bytes: unused
# * 20 bytes: SHA-1 hash of ignore patterns
TREE_METADATA = struct.Struct('>LLLLL4s20s')


# Must match the `Node` Rust struct in
# `rust/hg-core/src/dirstate_tree/on_disk.rs`. See doc-comments there.
#
# * 4 bytes: start offset of full path
# * 2 bytes: length of the full path
# * 2 bytes: length within the full path before its "base name"
# * 4 bytes: start offset of the copy source if any, or zero for no copy source
# * 2 bytes: length of the copy source if any, or unused
# * 4 bytes: start offset of child nodes
# * 4 bytes: number of child nodes
# * 4 bytes: number of descendant nodes that have an entry
# * 4 bytes: number of descendant nodes that have a "tracked" state
# * 1 byte: flags
# * 4 bytes: expected size
# * 4 bytes: mtime seconds
# * 4 bytes: mtime nanoseconds
NODE = struct.Struct('>LHHLHLLLLHlll')


assert TREE_METADATA_SIZE == TREE_METADATA.size
assert NODE_SIZE == NODE.size

# match constant in mercurial/pure/parsers.py
DIRSTATE_V2_DIRECTORY = 1 << 13


def parse_dirstate(map, copy_map, data, tree_metadata):
    """parse a full v2-dirstate from a binary data into dictionaries:

    - map: a {path: entry} mapping that will be filled
    - copy_map: a {path: copy-source} mapping that will be filled
    - data: a binary blob contains v2 nodes data
    - tree_metadata:: a binary blob of the top level node (from the docket)
    """
    (
        root_nodes_start,
        root_nodes_len,
        _nodes_with_entry_count,
        _nodes_with_copy_source_count,
        _unreachable_bytes,
        _unused,
        _ignore_patterns_hash,
    ) = TREE_METADATA.unpack(tree_metadata)
    parse_nodes(map, copy_map, data, root_nodes_start, root_nodes_len)


def parse_nodes(map, copy_map, data, start, len):
    """parse <len> nodes from <data> starting at offset <start>

    This is used by parse_dirstate to recursively fill `map` and `copy_map`.

    All directory specific information is ignored and do not need any
    processing (DIRECTORY, ALL_UNKNOWN_RECORDED, ALL_IGNORED_RECORDED)
    """
    for i in range(len):
        node_start = start + NODE_SIZE * i
        node_bytes = slice_with_len(data, node_start, NODE_SIZE)
        (
            path_start,
            path_len,
            _basename_start,
            copy_source_start,
            copy_source_len,
            children_start,
            children_count,
            _descendants_with_entry_count,
            _tracked_descendants_count,
            flags,
            size,
            mtime_s,
            mtime_ns,
        ) = NODE.unpack(node_bytes)

        # Parse child nodes of this node recursively
        parse_nodes(map, copy_map, data, children_start, children_count)

        item = parsers.DirstateItem.from_v2_data(flags, size, mtime_s, mtime_ns)
        if not item.any_tracked:
            continue
        path = slice_with_len(data, path_start, path_len)
        map[path] = item
        if copy_source_start:
            copy_map[path] = slice_with_len(
                data, copy_source_start, copy_source_len
            )


def slice_with_len(data, start, len):
    return data[start : start + len]


@attr.s
class Node:
    path = attr.ib()
    entry = attr.ib()
    parent = attr.ib(default=None)
    children_count = attr.ib(default=0)
    children_offset = attr.ib(default=0)
    descendants_with_entry = attr.ib(default=0)
    tracked_descendants = attr.ib(default=0)

    def pack(self, copy_map, paths_offset):
        path = self.path
        copy = copy_map.get(path)
        entry = self.entry

        path_start = paths_offset
        path_len = len(path)
        basename_start = path.rfind(b'/') + 1  # 0 if rfind returns -1
        if copy is not None:
            copy_source_start = paths_offset + len(path)
            copy_source_len = len(copy)
        else:
            copy_source_start = 0
            copy_source_len = 0
        if entry is not None:
            flags, size, mtime_s, mtime_ns = entry.v2_data()
        else:
            # There are no mtime-cached directories in the Python implementation
            flags = DIRSTATE_V2_DIRECTORY
            size = 0
            mtime_s = 0
            mtime_ns = 0
        return NODE.pack(
            path_start,
            path_len,
            basename_start,
            copy_source_start,
            copy_source_len,
            self.children_offset,
            self.children_count,
            self.descendants_with_entry,
            self.tracked_descendants,
            flags,
            size,
            mtime_s,
            mtime_ns,
        )


def pack_dirstate(map, copy_map):
    """
    Pack `map` and `copy_map` into the dirstate v2 binary format and return
    the tuple of (data, metadata) bytearrays.

    The on-disk format expects a tree-like structure where the leaves are
    written first (and sorted per-directory), going up levels until the root
    node and writing that one to the docket. See more details on the on-disk
    format in `mercurial/helptext/internals/dirstate-v2`.

    Since both `map` and `copy_map` are flat dicts we need to figure out the
    hierarchy. This algorithm does so without having to build the entire tree
    in-memory: it only keeps the minimum number of nodes around to satisfy the
    format.

    # Algorithm explanation

    This explanation does not talk about the different counters for tracked
    descendants and storing the copies, but that work is pretty simple once this
    algorithm is in place.

    ## Building a subtree

    First, sort `map`: this makes it so the leaves of the tree are contiguous
    per directory (i.e. a/b/c and a/b/d will be next to each other in the list),
    and enables us to use the ordering of folders to have a "cursor" of the
    current folder we're in without ever going twice in the same branch of the
    tree. The cursor is a node that remembers its parent and any information
    relevant to the format (see the `Node` class), building the relevant part
    of the tree lazily.
    Then, for each file in `map`, move the cursor into the tree to the
    corresponding folder of the file: for example, if the very first file
    is "a/b/c", we start from `Node[""]`, create `Node["a"]` which points to
    its parent `Node[""]`, then create `Node["a/b"]`, which points to its parent
    `Node["a"]`. These nodes are kept around in a stack.
    If the next file in `map` is in the same subtree ("a/b/d" or "a/b/e/f"), we
    add it to the stack and keep looping with the same logic of creating the
    tree nodes as needed. If however the next file in `map` is *not* in the same
    subtree ("a/other", if we're still in the "a/b" folder), then we know that
    the subtree we're in is complete.

    ## Writing the subtree

    We have the entire subtree in the stack, so we start writing it to disk
    folder by folder. The way we write a folder is to pop the stack into a list
    until the folder changes, revert this list of direct children (to satisfy
    the format requirement that children be sorted). This process repeats until
    we hit the "other" subtree.

    An example:
        a
        dir1/b
        dir1/c
        dir2/dir3/d
        dir2/dir3/e
        dir2/f

    Would have us:
        - add to the stack until "dir2/dir3/e"
        - realize that "dir2/f" is in a different subtree
        - pop "dir2/dir3/e", "dir2/dir3/d", reverse them so they're sorted and
          pack them since the next entry is "dir2/dir3"
        - go back up to "dir2"
        - add "dir2/f" to the stack
        - realize we're done with the map
        - pop "dir2/f", "dir2/dir3" from the stack, reverse and pack them
        - go up to the root node, do the same to write "a", "dir1" and "dir2" in
          that order

    ## Special case for the root node

    The root node is not serialized in the format, but its information is
    written to the docket. Again, see more details on the on-disk format in
    `mercurial/helptext/internals/dirstate-v2`.
    """
    data = bytearray()
    root_nodes_start = 0
    root_nodes_len = 0
    nodes_with_entry_count = 0
    nodes_with_copy_source_count = 0
    # Will always be 0 since this implementation always re-writes everything
    # to disk
    unreachable_bytes = 0
    unused = b'\x00' * 4
    # This is an optimization that's only useful for the Rust implementation
    ignore_patterns_hash = b'\x00' * 20

    if len(map) == 0:
        tree_metadata = TREE_METADATA.pack(
            root_nodes_start,
            root_nodes_len,
            nodes_with_entry_count,
            nodes_with_copy_source_count,
            unreachable_bytes,
            unused,
            ignore_patterns_hash,
        )
        return data, tree_metadata

    sorted_map = sorted(map.items(), key=lambda x: x[0].split(b"/"))

    # Use a stack to have to only remember the nodes we currently need
    # instead of building the entire tree in memory
    stack = []
    current_node = Node(b"", None)
    stack.append(current_node)

    for index, (path, entry) in enumerate(sorted_map, 1):
        nodes_with_entry_count += 1
        if path in copy_map:
            nodes_with_copy_source_count += 1
        current_folder = get_folder(path)
        current_node = move_to_correct_node_in_tree(
            current_folder, current_node, stack
        )

        current_node.children_count += 1
        # Entries from `map` are never `None`
        if entry.tracked:
            current_node.tracked_descendants += 1
        current_node.descendants_with_entry += 1
        stack.append(Node(path, entry, current_node))

        should_pack = True
        next_path = None
        if index < len(sorted_map):
            # Determine if the next entry is in the same sub-tree, if so don't
            # pack yet
            next_path = sorted_map[index][0]
            should_pack = not is_ancestor(next_path, current_folder)
        if should_pack:
            pack_directory_children(current_node, copy_map, data, stack)
            while stack and current_node.path != b"":
                # Go up the tree and write until we reach the folder of the next
                # entry (if any, otherwise the root)
                parent = current_node.parent
                in_ancestor_of_next_path = next_path is not None and (
                    is_ancestor(next_path, get_folder(stack[-1].path))
                )
                if parent is None or in_ancestor_of_next_path:
                    break
                pack_directory_children(parent, copy_map, data, stack)
                current_node = parent

    # Special case for the root node since we don't write it to disk, only its
    # children to the docket
    current_node = stack.pop()
    assert current_node.path == b"", current_node.path
    assert len(stack) == 0, len(stack)

    tree_metadata = TREE_METADATA.pack(
        current_node.children_offset,
        current_node.children_count,
        nodes_with_entry_count,
        nodes_with_copy_source_count,
        unreachable_bytes,
        unused,
        ignore_patterns_hash,
    )

    return data, tree_metadata


def get_folder(path):
    """
    Return the folder of the path that's given, an empty string for root paths.
    """
    return path.rsplit(b'/', 1)[0] if b'/' in path else b''


def is_ancestor(path, maybe_ancestor):
    """Returns whether `maybe_ancestor` is an ancestor of `path`.

    >>> is_ancestor(b"a", b"")
    True
    >>> is_ancestor(b"a/b/c", b"a/b/c")
    False
    >>> is_ancestor(b"hgext3rd/__init__.py", b"hgext")
    False
    >>> is_ancestor(b"hgext3rd/__init__.py", b"hgext3rd")
    True
    """
    if maybe_ancestor == b"":
        return True
    if path <= maybe_ancestor:
        return False
    path_components = path.split(b"/")
    ancestor_components = maybe_ancestor.split(b"/")
    return all(c == o for c, o in zip(path_components, ancestor_components))


def move_to_correct_node_in_tree(target_folder, current_node, stack):
    """
    Move inside the dirstate node tree to the node corresponding to
    `target_folder`, creating the missing nodes along the way if needed.
    """
    while target_folder != current_node.path:
        if is_ancestor(target_folder, current_node.path):
            # We need to go down a folder
            prefix = target_folder[len(current_node.path) :].lstrip(b'/')
            subfolder_name = prefix.split(b'/', 1)[0]
            if current_node.path:
                subfolder_path = current_node.path + b'/' + subfolder_name
            else:
                subfolder_path = subfolder_name
            next_node = stack[-1]
            if next_node.path == target_folder:
                # This folder is now a file and only contains removed entries
                # merge with the last node
                current_node = next_node
            else:
                current_node.children_count += 1
                current_node = Node(subfolder_path, None, current_node)
                stack.append(current_node)
        else:
            # We need to go up a folder
            current_node = current_node.parent
    return current_node


def pack_directory_children(node, copy_map, data, stack):
    """
    Write the binary representation of the direct sorted children of `node` to
    `data`
    """
    direct_children = []

    while stack[-1].path != b"" and get_folder(stack[-1].path) == node.path:
        direct_children.append(stack.pop())
    if not direct_children:
        raise error.ProgrammingError(b"no direct children for %r" % node.path)

    # Reverse the stack to get the correct sorted order
    direct_children.reverse()
    packed_children = bytearray()
    # Write the paths to `data`. Pack child nodes but don't write them yet
    for child in direct_children:
        packed = child.pack(copy_map=copy_map, paths_offset=len(data))
        packed_children.extend(packed)
        data.extend(child.path)
        data.extend(copy_map.get(child.path, b""))
        node.tracked_descendants += child.tracked_descendants
        node.descendants_with_entry += child.descendants_with_entry
    # Write the fixed-size child nodes all together
    node.children_offset = len(data)
    data.extend(packed_children)
