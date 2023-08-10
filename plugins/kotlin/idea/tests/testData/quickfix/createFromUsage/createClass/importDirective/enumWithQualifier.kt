// "Create enum 'A'" "true"
package p

import p.X.<caret>A

class X {

}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix