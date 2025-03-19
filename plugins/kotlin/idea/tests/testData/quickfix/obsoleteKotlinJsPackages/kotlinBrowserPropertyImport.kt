// "Fix 'kotlin.browser' package usage" "true"
// JS_WITH_DOM_API_COMPAT

package test

import kotlin.<caret>browser.localStorage
import kotlin.dom.addClass
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.migration.ObsoleteKotlinBrowserUsageFix