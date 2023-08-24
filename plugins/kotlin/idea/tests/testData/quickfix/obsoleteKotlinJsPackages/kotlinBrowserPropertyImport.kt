// "Fix 'kotlin.browser' package usage" "true"
// JS

package test

import kotlin.<caret>browser.localStorage
import kotlin.dom.addClass
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.migration.ObsoleteKotlinBrowserUsageFix