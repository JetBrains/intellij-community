// "Create enum constant 'A'" "true"
package p

import p.E.<caret>A

enum class E {

}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix