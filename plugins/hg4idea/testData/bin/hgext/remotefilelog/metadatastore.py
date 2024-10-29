from mercurial.node import (
    hex,
    sha1nodeconstants,
)
from . import (
    basestore,
    shallowutil,
)


class unionmetadatastore(basestore.baseunionstore):
    def __init__(self, *args, **kwargs):
        super(unionmetadatastore, self).__init__(*args, **kwargs)

        self.stores = args
        self.writestore = kwargs.get('writestore')

        # If allowincomplete==True then the union store can return partial
        # ancestor lists, otherwise it will throw a KeyError if a full
        # history can't be found.
        self.allowincomplete = kwargs.get('allowincomplete', False)

    def getancestors(self, name, node, known=None):
        """Returns as many ancestors as we're aware of.

        return value: {
           node: (p1, p2, linknode, copyfrom),
           ...
        }
        """
        if known is None:
            known = set()
        if node in known:
            return []

        ancestors = {}

        def traverse(curname, curnode):
            # TODO: this algorithm has the potential to traverse parts of
            # history twice. Ex: with A->B->C->F and A->B->D->F, both D and C
            # may be queued as missing, then B and A are traversed for both.
            queue = [(curname, curnode)]
            missing = []
            seen = set()
            while queue:
                name, node = queue.pop()
                if (name, node) in seen:
                    continue
                seen.add((name, node))
                value = ancestors.get(node)
                if not value:
                    missing.append((name, node))
                    continue
                p1, p2, linknode, copyfrom = value
                if p1 != sha1nodeconstants.nullid and p1 not in known:
                    queue.append((copyfrom or curname, p1))
                if p2 != sha1nodeconstants.nullid and p2 not in known:
                    queue.append((curname, p2))
            return missing

        missing = [(name, node)]
        while missing:
            curname, curnode = missing.pop()
            try:
                ancestors.update(
                    self._getpartialancestors(curname, curnode, known=known)
                )
                newmissing = traverse(curname, curnode)
                missing.extend(newmissing)
            except KeyError:
                # If we allow incomplete histories, don't throw.
                if not self.allowincomplete:
                    raise
                # If the requested name+node doesn't exist, always throw.
                if (curname, curnode) == (name, node):
                    raise

        # TODO: ancestors should probably be (name, node) -> (value)
        return ancestors

    @basestore.baseunionstore.retriable
    def _getpartialancestors(self, name, node, known=None):
        for store in self.stores:
            try:
                return store.getancestors(name, node, known=known)
            except KeyError:
                pass

        raise KeyError((name, hex(node)))

    @basestore.baseunionstore.retriable
    def getnodeinfo(self, name, node):
        for store in self.stores:
            try:
                return store.getnodeinfo(name, node)
            except KeyError:
                pass

        raise KeyError((name, hex(node)))

    def add(self, name, node, data):
        raise RuntimeError(
            b"cannot add content only to remotefilelog contentstore"
        )

    def getmissing(self, keys):
        missing = keys
        for store in self.stores:
            if missing:
                missing = store.getmissing(missing)
        return missing

    def markledger(self, ledger, options=None):
        for store in self.stores:
            store.markledger(ledger, options)

    def getmetrics(self):
        metrics = [s.getmetrics() for s in self.stores]
        return shallowutil.sumdicts(*metrics)


class remotefilelogmetadatastore(basestore.basestore):
    def getancestors(self, name, node, known=None):
        """Returns as many ancestors as we're aware of.

        return value: {
           node: (p1, p2, linknode, copyfrom),
           ...
        }
        """
        data = self._getdata(name, node)
        ancestors = shallowutil.ancestormap(data)
        return ancestors

    def getnodeinfo(self, name, node):
        return self.getancestors(name, node)[node]

    def add(self, name, node, parents, linknode):
        raise RuntimeError(
            b"cannot add metadata only to remotefilelog metadatastore"
        )


class remotemetadatastore:
    def __init__(self, ui, fileservice, shared):
        self._fileservice = fileservice
        self._shared = shared

    def getancestors(self, name, node, known=None):
        self._fileservice.prefetch(
            [(name, hex(node))], force=True, fetchdata=False, fetchhistory=True
        )
        return self._shared.getancestors(name, node, known=known)

    def getnodeinfo(self, name, node):
        return self.getancestors(name, node)[node]

    def add(self, name, node, data):
        raise RuntimeError(b"cannot add to a remote store")

    def getmissing(self, keys):
        return keys

    def markledger(self, ledger, options=None):
        pass
