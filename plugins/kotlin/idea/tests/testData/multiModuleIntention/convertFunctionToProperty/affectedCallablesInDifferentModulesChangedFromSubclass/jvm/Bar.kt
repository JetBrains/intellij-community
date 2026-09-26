// "Convert function to property" "true"
// PRIORITY: LOW
package jvm

import common.Foo

class Bar : Foo { override fun foo<caret>() = "" }