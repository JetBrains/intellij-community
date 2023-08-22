// "Fix 'kotlin.browser' package usage" "true"
// JS

package test

import kotlin.<caret>browser.*
import kotlin.dom.*

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.migration.ObsoleteKotlinBrowserUsageFix