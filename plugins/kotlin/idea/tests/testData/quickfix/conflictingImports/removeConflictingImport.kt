// "Remove conflicting import for 'java.util.ArrayList'" "true"
package test

import java.util.ArrayList<caret>
import java.util.HashMap as ArrayList

fun foo(a : ArrayList<String, String>) {

}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemovePsiElementSimpleFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemovePsiElementSimpleFix