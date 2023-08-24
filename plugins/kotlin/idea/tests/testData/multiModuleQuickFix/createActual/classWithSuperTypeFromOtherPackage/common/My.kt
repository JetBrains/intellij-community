// "Add missing actual declarations" "true"
// DISABLE-ERRORS
// IGNORE_K2

package my

import other.Another
import other.Other

expect class My<caret> : Other<Another>