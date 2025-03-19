// IGNORE_K1
package first

import second.One.<caret>

// EXIST: {"lookupString":"someVal","typeText":"Int","icon":"org/jetbrains/kotlin/idea/icons/field_value.svg","attributes":"","allLookupStrings":"getSomeVal, someVal","itemText":"someVal"}
// EXIST: {"lookupString":"someFun","tailText":"()","typeText":"Unit","icon":"Method","attributes":"","allLookupStrings":"someFun","itemText":"someFun"}
// EXIST: {"lookupString":"equals","tailText":"(other: Any?)","typeText":"Boolean","icon":"Method","attributes":"","allLookupStrings":"equals","itemText":"equals"}
// EXIST: {"lookupString":"hashCode","tailText":"()","typeText":"Int","icon":"Method","attributes":"","allLookupStrings":"hashCode","itemText":"hashCode"}
// EXIST: {"lookupString":"toString","tailText":"()","typeText":"String","icon":"Method","attributes":"","allLookupStrings":"toString","itemText":"toString"}
// ABSENT: staticFromJava