# dagparser.py - parser and generator for concise description of DAGs
#
# Copyright 2010 Peter Arrenbrecht <peter@arrenbrecht.ch>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import re
import string

from .i18n import _
from . import (
    error,
    pycompat,
)
from .utils import stringutil


def parsedag(desc):
    '''parses a DAG from a concise textual description; generates events

    "+n" is a linear run of n nodes based on the current default parent
    "." is a single node based on the current default parent
    "$" resets the default parent to -1 (implied at the start);
        otherwise the default parent is always the last node created
    "<p" sets the default parent to the backref p
    "*p" is a fork at parent p, where p is a backref
    "*p1/p2/.../pn" is a merge of parents p1..pn, where the pi are backrefs
    "/p2/.../pn" is a merge of the preceding node and p2..pn
    ":name" defines a label for the preceding node; labels can be redefined
    "@text" emits an annotation event for text
    "!command" emits an action event for the current node
    "!!my command\n" is like "!", but to the end of the line
    "#...\n" is a comment up to the end of the line

    Whitespace between the above elements is ignored.

    A backref is either
     * a number n, which references the node curr-n, where curr is the current
       node, or
     * the name of a label you placed earlier using ":name", or
     * empty to denote the default parent.

    All string valued-elements are either strictly alphanumeric, or must
    be enclosed in double quotes ("..."), with "\" as escape character.

    Generates sequence of

      ('n', (id, [parentids])) for node creation
      ('l', (id, labelname)) for labels on nodes
      ('a', text) for annotations
      ('c', command) for actions (!)
      ('C', command) for line actions (!!)

    Examples
    --------

    Example of a complex graph (output not shown for brevity):

        >>> len(list(parsedag(b"""
        ...
        ... +3         # 3 nodes in linear run
        ... :forkhere  # a label for the last of the 3 nodes from above
        ... +5         # 5 more nodes on one branch
        ... :mergethis # label again
        ... <forkhere  # set default parent to labeled fork node
        ... +10        # 10 more nodes on a parallel branch
        ... @stable    # following nodes will be annotated as "stable"
        ... +5         # 5 nodes in stable
        ... !addfile   # custom command; could trigger new file in next node
        ... +2         # two more nodes
        ... /mergethis # merge last node with labeled node
        ... +4         # 4 more nodes descending from merge node
        ...
        ... """)))
        34

    Empty list:

        >>> list(parsedag(b""))
        []

    A simple linear run:

        >>> list(parsedag(b"+3"))
        [('n', (0, [-1])), ('n', (1, [0])), ('n', (2, [1]))]

    Some non-standard ways to define such runs:

        >>> list(parsedag(b"+1+2"))
        [('n', (0, [-1])), ('n', (1, [0])), ('n', (2, [1]))]

        >>> list(parsedag(b"+1*1*"))
        [('n', (0, [-1])), ('n', (1, [0])), ('n', (2, [1]))]

        >>> list(parsedag(b"*"))
        [('n', (0, [-1]))]

        >>> list(parsedag(b"..."))
        [('n', (0, [-1])), ('n', (1, [0])), ('n', (2, [1]))]

    A fork and a join, using numeric back references:

        >>> list(parsedag(b"+2*2*/2"))
        [('n', (0, [-1])), ('n', (1, [0])), ('n', (2, [0])), ('n', (3, [2, 1]))]

        >>> list(parsedag(b"+2<2+1/2"))
        [('n', (0, [-1])), ('n', (1, [0])), ('n', (2, [0])), ('n', (3, [2, 1]))]

    Placing a label:

        >>> list(parsedag(b"+1 :mylabel +1"))
        [('n', (0, [-1])), ('l', (0, 'mylabel')), ('n', (1, [0]))]

    An empty label (silly, really):

        >>> list(parsedag(b"+1:+1"))
        [('n', (0, [-1])), ('l', (0, '')), ('n', (1, [0]))]

    Fork and join, but with labels instead of numeric back references:

        >>> list(parsedag(b"+1:f +1:p2 *f */p2"))
        [('n', (0, [-1])), ('l', (0, 'f')), ('n', (1, [0])), ('l', (1, 'p2')),
         ('n', (2, [0])), ('n', (3, [2, 1]))]

        >>> list(parsedag(b"+1:f +1:p2 <f +1 /p2"))
        [('n', (0, [-1])), ('l', (0, 'f')), ('n', (1, [0])), ('l', (1, 'p2')),
         ('n', (2, [0])), ('n', (3, [2, 1]))]

    Restarting from the root:

        >>> list(parsedag(b"+1 $ +1"))
        [('n', (0, [-1])), ('n', (1, [-1]))]

    Annotations, which are meant to introduce sticky state for subsequent nodes:

        >>> list(parsedag(b"+1 @ann +1"))
        [('n', (0, [-1])), ('a', 'ann'), ('n', (1, [0]))]

        >>> list(parsedag(b'+1 @"my annotation" +1'))
        [('n', (0, [-1])), ('a', 'my annotation'), ('n', (1, [0]))]

    Commands, which are meant to operate on the most recently created node:

        >>> list(parsedag(b"+1 !cmd +1"))
        [('n', (0, [-1])), ('c', 'cmd'), ('n', (1, [0]))]

        >>> list(parsedag(b'+1 !"my command" +1'))
        [('n', (0, [-1])), ('c', 'my command'), ('n', (1, [0]))]

        >>> list(parsedag(b'+1 !!my command line\\n +1'))
        [('n', (0, [-1])), ('C', 'my command line'), ('n', (1, [0]))]

    Comments, which extend to the end of the line:

        >>> list(parsedag(b'+1 # comment\\n+1'))
        [('n', (0, [-1])), ('n', (1, [0]))]

    Error:

        >>> try: list(parsedag(b'+1 bad'))
        ... except Exception as e: print(pycompat.sysstr(bytes(e)))
        invalid character in dag description: bad...

    '''
    if not desc:
        return

    # pytype: disable=wrong-arg-types
    wordchars = pycompat.bytestr(string.ascii_letters + string.digits)
    # pytype: enable=wrong-arg-types

    labels = {}
    p1 = -1
    r = 0

    def resolve(ref):
        if not ref:
            return p1
        # pytype: disable=wrong-arg-types
        elif ref[0] in pycompat.bytestr(string.digits):
            # pytype: enable=wrong-arg-types
            return r - int(ref)
        else:
            return labels[ref]

    chiter = pycompat.iterbytestr(desc)

    def nextch():
        return next(chiter, b'\0')

    def nextrun(c, allow):
        s = b''
        while c in allow:
            s += c
            c = nextch()
        return c, s

    def nextdelimited(c, limit, escape):
        s = b''
        while c != limit:
            if c == escape:
                c = nextch()
            s += c
            c = nextch()
        return nextch(), s

    def nextstring(c):
        if c == b'"':
            return nextdelimited(nextch(), b'"', b'\\')
        else:
            return nextrun(c, wordchars)

    c = nextch()
    while c != b'\0':
        # pytype: disable=wrong-arg-types
        while c in pycompat.bytestr(string.whitespace):
            # pytype: enable=wrong-arg-types
            c = nextch()
        if c == b'.':
            yield b'n', (r, [p1])
            p1 = r
            r += 1
            c = nextch()
        elif c == b'+':
            # pytype: disable=wrong-arg-types
            c, digs = nextrun(nextch(), pycompat.bytestr(string.digits))
            # pytype: enable=wrong-arg-types
            n = int(digs)
            for i in range(0, n):
                yield b'n', (r, [p1])
                p1 = r
                r += 1
        elif c in b'*/':
            if c == b'*':
                c = nextch()
            c, pref = nextstring(c)
            prefs = [pref]
            while c == b'/':
                c, pref = nextstring(nextch())
                prefs.append(pref)
            ps = [resolve(ref) for ref in prefs]
            yield b'n', (r, ps)
            p1 = r
            r += 1
        elif c == b'<':
            c, ref = nextstring(nextch())
            p1 = resolve(ref)
        elif c == b':':
            c, name = nextstring(nextch())
            labels[name] = p1
            yield b'l', (p1, name)
        elif c == b'@':
            c, text = nextstring(nextch())
            yield b'a', text
        elif c == b'!':
            c = nextch()
            if c == b'!':
                cmd = b''
                c = nextch()
                while c not in b'\n\r\0':
                    cmd += c
                    c = nextch()
                yield b'C', cmd
            else:
                c, cmd = nextstring(c)
                yield b'c', cmd
        elif c == b'#':
            while c not in b'\n\r\0':
                c = nextch()
        elif c == b'$':
            p1 = -1
            c = nextch()
        elif c == b'\0':
            return  # in case it was preceded by whitespace
        else:
            s = b''
            i = 0
            while c != b'\0' and i < 10:
                s += c
                i += 1
                c = nextch()
            raise error.Abort(
                _(b'invalid character in dag description: %s...') % s
            )


def dagtextlines(
    events,
    addspaces=True,
    wraplabels=False,
    wrapannotations=False,
    wrapcommands=False,
    wrapnonlinear=False,
    usedots=False,
    maxlinewidth=70,
):
    '''generates single lines for dagtext()'''

    def wrapstring(text):
        if re.match(b"^[0-9a-z]*$", text):
            return text
        return b'"' + text.replace(b'\\', b'\\\\').replace(b'"', b'\"') + b'"'

    def gen():
        labels = {}
        run = 0
        wantr = 0
        needroot = False
        for kind, data in events:
            if kind == b'n':
                r, ps = data

                # sanity check
                if r != wantr:
                    raise error.Abort(_(b"expected id %i, got %i") % (wantr, r))
                if not ps:
                    ps = [-1]
                else:
                    for p in ps:
                        if p >= r:
                            raise error.Abort(
                                _(
                                    b"parent id %i is larger than "
                                    b"current id %i"
                                )
                                % (p, r)
                            )
                wantr += 1

                # new root?
                p1 = r - 1
                if len(ps) == 1 and ps[0] == -1:
                    if needroot:
                        if run:
                            yield b'+%d' % run
                            run = 0
                        if wrapnonlinear:
                            yield b'\n'
                        yield b'$'
                        p1 = -1
                    else:
                        needroot = True
                if len(ps) == 1 and ps[0] == p1:
                    if usedots:
                        yield b"."
                    else:
                        run += 1
                else:
                    if run:
                        yield b'+%d' % run
                        run = 0
                    if wrapnonlinear:
                        yield b'\n'
                    prefs = []
                    for p in ps:
                        if p == p1:
                            prefs.append(b'')
                        elif p in labels:
                            prefs.append(labels[p])
                        else:
                            prefs.append(b'%d' % (r - p))
                    yield b'*' + b'/'.join(prefs)
            else:
                if run:
                    yield b'+%d' % run
                    run = 0
                if kind == b'l':
                    rid, name = data
                    labels[rid] = name
                    yield b':' + name
                    if wraplabels:
                        yield b'\n'
                elif kind == b'c':
                    yield b'!' + wrapstring(data)
                    if wrapcommands:
                        yield b'\n'
                elif kind == b'C':
                    yield b'!!' + data
                    yield b'\n'
                elif kind == b'a':
                    if wrapannotations:
                        yield b'\n'
                    yield b'@' + wrapstring(data)
                elif kind == b'#':
                    yield b'#' + data
                    yield b'\n'
                else:
                    raise error.Abort(
                        _(b"invalid event type in dag: ('%s', '%s')")
                        % (
                            stringutil.escapestr(kind),
                            stringutil.escapestr(data),
                        )
                    )
        if run:
            yield b'+%d' % run

    line = b''
    for part in gen():
        if part == b'\n':
            if line:
                yield line
                line = b''
        else:
            if len(line) + len(part) >= maxlinewidth:
                yield line
                line = b''
            elif addspaces and line and part != b'.':
                line += b' '
            line += part
    if line:
        yield line


def dagtext(
    dag,
    addspaces=True,
    wraplabels=False,
    wrapannotations=False,
    wrapcommands=False,
    wrapnonlinear=False,
    usedots=False,
    maxlinewidth=70,
):
    """generates lines of a textual representation for a dag event stream

    events should generate what parsedag() does, so:

      ('n', (id, [parentids])) for node creation
      ('l', (id, labelname)) for labels on nodes
      ('a', text) for annotations
      ('c', text) for commands
      ('C', text) for line commands ('!!')
      ('#', text) for comment lines

    Parent nodes must come before child nodes.

    Examples
    --------

    Linear run:

        >>> dagtext([(b'n', (0, [-1])), (b'n', (1, [0]))])
        '+2'

    Two roots:

        >>> dagtext([(b'n', (0, [-1])), (b'n', (1, [-1]))])
        '+1 $ +1'

    Fork and join:

        >>> dagtext([(b'n', (0, [-1])), (b'n', (1, [0])), (b'n', (2, [0])),
        ...          (b'n', (3, [2, 1]))])
        '+2 *2 */2'

    Fork and join with labels:

        >>> dagtext([(b'n', (0, [-1])), (b'l', (0, b'f')), (b'n', (1, [0])),
        ...          (b'l', (1, b'p2')), (b'n', (2, [0])), (b'n', (3, [2, 1]))])
        '+1 :f +1 :p2 *f */p2'

    Annotations:

        >>> dagtext([(b'n', (0, [-1])), (b'a', b'ann'), (b'n', (1, [0]))])
        '+1 @ann +1'

        >>> dagtext([(b'n', (0, [-1])),
        ...          (b'a', b'my annotation'),
        ...          (b'n', (1, [0]))])
        '+1 @"my annotation" +1'

    Commands:

        >>> dagtext([(b'n', (0, [-1])), (b'c', b'cmd'), (b'n', (1, [0]))])
        '+1 !cmd +1'

        >>> dagtext([(b'n', (0, [-1])),
        ...          (b'c', b'my command'),
        ...          (b'n', (1, [0]))])
        '+1 !"my command" +1'

        >>> dagtext([(b'n', (0, [-1])),
        ...          (b'C', b'my command line'),
        ...          (b'n', (1, [0]))])
        '+1 !!my command line\\n+1'

    Comments:

        >>> dagtext([(b'n', (0, [-1])), (b'#', b' comment'), (b'n', (1, [0]))])
        '+1 # comment\\n+1'

        >>> dagtext([])
        ''

    Combining parsedag and dagtext:

        >>> dagtext(parsedag(b'+1 :f +1 :p2 *f */p2'))
        '+1 :f +1 :p2 *f */p2'

    """
    return b"\n".join(
        dagtextlines(
            dag,
            addspaces,
            wraplabels,
            wrapannotations,
            wrapcommands,
            wrapnonlinear,
            usedots,
            maxlinewidth,
        )
    )
