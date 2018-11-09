print '\n'
print <error descr="Illegal escape character in string literal">'\u000a'</error>
print <error descr="Illegal escape character in string literal">"\u000d"</error>
print <error descr="Illegal escape character in string literal">'\y'</error>
ptint <error descr="Illegal escape character in string literal">"\n\a\t"</error>
print "<error descr="Illegal escape character in string literal">\n\a</error>${x}\t"
print "\n${x}\t"
print "\n${x}\"<EOLError descr="Gstring end expected"></EOLError>
print "\n\"<EOLError descr="Gstring end expected"></EOLError>
print '''\n'''
print <error descr="Illegal escape character in string literal">'''\y'''</error>
print """<error descr="Illegal escape character in string literal">\n\a</error>${x}\t"""
ptint """<error descr="Illegal escape character in string literal">""\n\a\t</error>"""
print """\n${x}\t"""
print "dfg\$fg"
print 'fg\$fg'
print (<error descr="Illegal escape character in string literal">/abc\n\r\y\o \u12 /</error>)
print (/abc\n\r\y\o \u1234 /)
print '\123\123'
print '\198'
print """\n${x}\"""
<EOLError descr="Gstring end expected"></EOLError>