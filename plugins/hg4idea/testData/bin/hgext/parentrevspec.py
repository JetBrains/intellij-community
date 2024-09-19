# Mercurial extension to make it easy to refer to the parent of a revision
#
# Copyright (C) 2007 Alexis S. L. Carvalho <alexis@cecm.usp.br>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

'''interpret suffixes to refer to ancestor revisions

This extension allows you to use git-style suffixes to refer to the
ancestors of a specific revision.

For example, if you can refer to a revision as "foo", then::

  foo^N = Nth parent of foo
  foo^0 = foo
  foo^1 = first parent of foo
  foo^2 = second parent of foo
  foo^  = foo^1

  foo~N = Nth first grandparent of foo
  foo~0 = foo
  foo~1 = foo^1 = foo^ = first parent of foo
  foo~2 = foo^1^1 = foo^^ = first parent of first parent of foo
'''
from mercurial import error

def reposetup(ui, repo):
    if not repo.local():
        return

    class parentrevspecrepo(repo.__class__):
        def lookup(self, key):
            try:
                _super = super(parentrevspecrepo, self)
                return _super.lookup(key)
            except error.RepoError:
                pass

            circ = key.find('^')
            tilde = key.find('~')
            if circ < 0 and tilde < 0:
                raise
            elif circ >= 0 and tilde >= 0:
                end = min(circ, tilde)
            else:
                end = max(circ, tilde)

            cl = self.changelog
            base = key[:end]
            try:
                node = _super.lookup(base)
            except error.RepoError:
                # eek - reraise the first error
                return _super.lookup(key)

            rev = cl.rev(node)
            suffix = key[end:]
            i = 0
            while i < len(suffix):
                # foo^N => Nth parent of foo
                # foo^0 == foo
                # foo^1 == foo^ == 1st parent of foo
                # foo^2 == 2nd parent of foo
                if suffix[i] == '^':
                    j = i + 1
                    p = cl.parentrevs(rev)
                    if j < len(suffix) and suffix[j].isdigit():
                        j += 1
                        n = int(suffix[i + 1:j])
                        if n > 2 or n == 2 and p[1] == -1:
                            raise
                    else:
                        n = 1
                    if n:
                        rev = p[n - 1]
                    i = j
                # foo~N => Nth first grandparent of foo
                # foo~0 = foo
                # foo~1 = foo^1 == foo^ == 1st parent of foo
                # foo~2 = foo^1^1 == foo^^ == 1st parent of 1st parent of foo
                elif suffix[i] == '~':
                    j = i + 1
                    while j < len(suffix) and suffix[j].isdigit():
                        j += 1
                    if j == i + 1:
                        raise
                    n = int(suffix[i + 1:j])
                    for k in xrange(n):
                        rev = cl.parentrevs(rev)[0]
                    i = j
                else:
                    raise
            return cl.node(rev)

    repo.__class__ = parentrevspecrepo
