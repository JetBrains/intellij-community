// "Import" "true"
// ERROR: Cannot access 'F': it is private in file
// ERROR: Cannot access 'F': it is private in file

package my.pack

import simple.F

@F<caret>()
class A
