print '\n'
print <error descr="Illegal escape character in string literal">'\y'</error>
ptint <error descr="Illegal escape character in string literal">"\n\a\t"</error>
print "\n\a${x}\t"
print "\n${x}\t"
print "\n${x}\"<EOLError descr="String end expected"></EOLError>
print "\n\"<EOLError descr="String end expected"></EOLError>
print '''\n'''
print <error descr="Illegal escape character in string literal">'''\y'''</error>
print """\n\a${x}\t"""
ptint """""\n\a\t"""
print """\n${x}\t"""
print "dfg\$fg"
print 'fg\$fg'
print """\n${x}\"""<EOLError descr="String end expected"></EOLError>