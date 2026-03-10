// "Remove conflicting import for 'java.util.ArrayList'" "true"
// K2_ERROR: Conflicting import: imported name 'ArrayList' is ambiguous.
// K2_ERROR: Conflicting import: imported name 'ArrayList' is ambiguous.
package test

import java.util.ArrayList as foo
import java.util.ArrayList as foo<caret>
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemovePsiElementSimpleFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemovePsiElementSimpleFix