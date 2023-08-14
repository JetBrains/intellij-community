// "Fix 'kotlin.dom' package usage" "true"
// JS_WITH_DOM_API_COMPAT

package test

import kotlin.browser.localStorage
import kotlin.<caret>dom.addClass
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.migration.ObsoleteKotlinDomUsageFix