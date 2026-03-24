// IGNORE_K1
package testPack

import testPack.MyClass.Companion.GENERIC_ARGUMENT

class MyClass {
   companion object {
      /**
       * [GENERIC_ARGUMENT]
       */
      val GENERIC_ARGUMENT = MyClass()
   }
}
