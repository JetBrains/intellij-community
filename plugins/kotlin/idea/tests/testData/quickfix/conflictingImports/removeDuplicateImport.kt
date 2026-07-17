// "Remove conflicting import for 'java.util.ArrayList'" "true"
// K2_ERROR: CONFLICTING_IMPORT
// K2_ERROR: CONFLICTING_IMPORT
package test

import java.util.ArrayList
import java.util.ArrayList<caret>
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemovePsiElementSimpleFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemovePsiElementSimpleFix