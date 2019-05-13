# formatter.py - generic output formatting for mercurial
#
# Copyright 2012 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

class baseformatter(object):
    def __init__(self, ui, topic, opts):
        self._ui = ui
        self._topic = topic
        self._style = opts.get("style")
        self._template = opts.get("template")
        self._item = None
    def __bool__(self):
        '''return False if we're not doing real templating so we can
        skip extra work'''
        return True
    def _showitem(self):
        '''show a formatted item once all data is collected'''
        pass
    def startitem(self):
        '''begin an item in the format list'''
        if self._item is not None:
            self._showitem()
        self._item = {}
    def data(self, **data):
        '''insert data into item that's not shown in default output'''
        self._item.update(data)
    def write(self, fields, deftext, *fielddata, **opts):
        '''do default text output while assigning data to item'''
        for k, v in zip(fields.split(), fielddata):
            self._item[k] = v
    def condwrite(self, cond, fields, deftext, *fielddata, **opts):
        '''do conditional write (primarily for plain formatter)'''
        for k, v in zip(fields.split(), fielddata):
            self._item[k] = v
    def plain(self, text, **opts):
        '''show raw text for non-templated mode'''
        pass
    def end(self):
        '''end output for the formatter'''
        if self._item is not None:
            self._showitem()

class plainformatter(baseformatter):
    '''the default text output scheme'''
    def __init__(self, ui, topic, opts):
        baseformatter.__init__(self, ui, topic, opts)
    def __bool__(self):
        return False
    def startitem(self):
        pass
    def data(self, **data):
        pass
    def write(self, fields, deftext, *fielddata, **opts):
        self._ui.write(deftext % fielddata, **opts)
    def condwrite(self, cond, fields, deftext, *fielddata, **opts):
        '''do conditional write'''
        if cond:
            self._ui.write(deftext % fielddata, **opts)
    def plain(self, text, **opts):
        self._ui.write(text, **opts)
    def end(self):
        pass

class debugformatter(baseformatter):
    def __init__(self, ui, topic, opts):
        baseformatter.__init__(self, ui, topic, opts)
        self._ui.write("%s = {\n" % self._topic)
    def _showitem(self):
        self._ui.write("    " + repr(self._item) + ",\n")
    def end(self):
        baseformatter.end(self)
        self._ui.write("}\n")

def formatter(ui, topic, opts):
    if ui.configbool('ui', 'formatdebug'):
        return debugformatter(ui, topic, opts)
    return plainformatter(ui, topic, opts)
