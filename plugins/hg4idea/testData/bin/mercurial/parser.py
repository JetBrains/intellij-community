# parser.py - simple top-down operator precedence parser for mercurial
#
# Copyright 2010 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

# see http://effbot.org/zone/simple-top-down-parsing.htm and
# http://eli.thegreenplace.net/2010/01/02/top-down-operator-precedence-parsing/
# for background

# takes a tokenizer and elements
# tokenizer is an iterator that returns type, value pairs
# elements is a mapping of types to binding strength, prefix and infix actions
# an action is a tree node name, a tree label, and an optional match
# __call__(program) parses program into a labeled tree

import error
from i18n import _

class parser(object):
    def __init__(self, tokenizer, elements, methods=None):
        self._tokenizer = tokenizer
        self._elements = elements
        self._methods = methods
        self.current = None
    def _advance(self):
        'advance the tokenizer'
        t = self.current
        try:
            self.current = self._iter.next()
        except StopIteration:
            pass
        return t
    def _match(self, m, pos):
        'make sure the tokenizer matches an end condition'
        if self.current[0] != m:
            raise error.ParseError(_("unexpected token: %s") % self.current[0],
                                   self.current[2])
        self._advance()
    def _parse(self, bind=0):
        token, value, pos = self._advance()
        # handle prefix rules on current token
        prefix = self._elements[token][1]
        if not prefix:
            raise error.ParseError(_("not a prefix: %s") % token, pos)
        if len(prefix) == 1:
            expr = (prefix[0], value)
        else:
            if len(prefix) > 2 and prefix[2] == self.current[0]:
                self._match(prefix[2], pos)
                expr = (prefix[0], None)
            else:
                expr = (prefix[0], self._parse(prefix[1]))
                if len(prefix) > 2:
                    self._match(prefix[2], pos)
        # gather tokens until we meet a lower binding strength
        while bind < self._elements[self.current[0]][0]:
            token, value, pos = self._advance()
            e = self._elements[token]
            # check for suffix - next token isn't a valid prefix
            if len(e) == 4 and not self._elements[self.current[0]][1]:
                suffix = e[3]
                expr = (suffix[0], expr)
            else:
                # handle infix rules
                if len(e) < 3 or not e[2]:
                    raise error.ParseError(_("not an infix: %s") % token, pos)
                infix = e[2]
                if len(infix) == 3 and infix[2] == self.current[0]:
                    self._match(infix[2], pos)
                    expr = (infix[0], expr, (None))
                else:
                    expr = (infix[0], expr, self._parse(infix[1]))
                    if len(infix) == 3:
                        self._match(infix[2], pos)
        return expr
    def parse(self, message):
        'generate a parse tree from a message'
        self._iter = self._tokenizer(message)
        self._advance()
        res = self._parse()
        token, value, pos = self.current
        return res, pos
    def eval(self, tree):
        'recursively evaluate a parse tree using node methods'
        if not isinstance(tree, tuple):
            return tree
        return self._methods[tree[0]](*[self.eval(t) for t in tree[1:]])
    def __call__(self, message):
        'parse a message into a parse tree and evaluate if methods given'
        t = self.parse(message)
        if self._methods:
            return self.eval(t)
        return t
