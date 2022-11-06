# simple strings
"double quote"
'single quote'
"""doc string"""
'''doc string with single quotes'''
"""
doc string with new line
"""
'''
doc string with single quotes and new line
'''
"dq" + 'sq' + "dq" + 'sq' + """ds""" + 'sq' + "dq"

# Strings with escaped chars
# Double quotes
"double \t quote \n with \r escaped \\ chars"
"double \" \' \\\' \\\" \\\\ \\ "
"double \" ' quote with escaped dq"
"double \" \' quote with escaped dq"
"double ' quote with sq"

# Single quotes
'single \t quote \n with \r escaped \\ chars'
'single \" \' \\\' \\\" \\\\ \\ '
'single " quote with escaped dq'
'single \' quote with sq'

# f-strings
f"double \t quote \n with \r escaped \\ chars"
f'single \t quote \n with \r escaped \\ chars'
f"double \" \' \\\' \\\" \\\\ \\ "
f'single \" \' \\\' \\\" \\\\ \\ '
f"double ' quotes with single"
f'single " quotes with double'
f"functional {1+1} string {'1'+'1'}"
f'functional {1+1} string {"1"+"1"}'

# r-strings
r"double \t quote \n with \r escaped \\ chars"
r'single \t quote \n with \r escaped \\ chars'
r"double \" \' \\\' \\\" \\\\ \\ "
r'single \" \' \\\' \\\" \\\\ \\ '
r"double ' quotes with single"
r'single " quotes with double'

# rf-strings
rf"double \t quote \n with \r escaped \\ chars"
rf'single \t quote \n with \r escaped \\ chars'
rf"double \" \' \\\' \\\" \\\\ \\ "
rf'single \" \' \\\' \\\" \\\\ \\ '
rf"double ' quotes with single"
rf'single " quotes with double'

# fr-strings
fr"double \t quote \n with \r escaped \\ chars"
fr'single \t quote \n with \r escaped \\ chars'
fr"double \" \' \\\' \\\" \\\\ \\ "
fr'single \" \' \\\' \\\" \\\\ \\ '
fr"double ' quotes with single"
fr'single " quotes with double'
fr"functional {1+1} string {'1'+'1'}"
fr'functional {1+1} string {"1"+"1"}'
