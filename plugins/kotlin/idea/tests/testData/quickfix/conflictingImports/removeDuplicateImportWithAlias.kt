// "Remove conflicting import for 'java.util.ArrayList'" "true"
package test

import java.util.ArrayList as foo
import java.util.ArrayList as foo<caret>
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemovePsiElementSimpleFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemovePsiElementSimpleFix