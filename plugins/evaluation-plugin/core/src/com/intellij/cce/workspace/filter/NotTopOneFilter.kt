//package com.intellij.cce.filter
//
//import com.intellij.cce.core.Lookup
//
//class NotTopOneFilter(override val evaluationType: String, override val name: String) : CompareSessionsFilter {
//    override val filterType = CompareSessionsFilter.CompareFilterType.NOT_TOP_ONE
//
//    override fun check(base: Lookup, forComparing: Lookup, text: String): Boolean {
//        val basePosition = base.suggestions.indexOfFirst { text == it.text }
//        return basePosition > 0
//    }
//}