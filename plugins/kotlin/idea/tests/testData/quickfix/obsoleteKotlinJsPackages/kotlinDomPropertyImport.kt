// "Fix 'kotlin.dom' package usage" "true"
// JS

package test

import kotlin.browser.localStorage
import kotlin.<caret>dom.addClass
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.migration.ObsoleteKotlinDomUsageFix