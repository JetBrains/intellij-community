import com.intellij.testFramework.LightProjectDescriptor

object SingletonDescriptor : LightProjectDescriptor()

data class DataDescriptor(val level: Int) : LightProjectDescriptor()

class WithEqualsAndHashCode : LightProjectDescriptor() {
  override fun equals(other: Any?): Boolean = other is WithEqualsAndHashCode
  override fun hashCode(): Int = 1
}

abstract class AbstractKtDescriptor : LightProjectDescriptor()
