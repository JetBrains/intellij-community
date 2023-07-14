// "Fix 'kotlin.dom' package usage" "true"
// JS

package test

import kotlin.browser.*
import kotlin.<caret>dom.*

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.migration.ObsoleteKotlinDomUsageFix