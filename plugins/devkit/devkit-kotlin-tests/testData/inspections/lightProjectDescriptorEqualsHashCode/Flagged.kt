import com.intellij.testFramework.LightProjectDescriptor

class <error descr="Test project descriptor 'FlaggedKt' does not override 'equals()' and 'hashCode()', so its non-static instances are not reused between tests">FlaggedKt</error> : LightProjectDescriptor()

class <error descr="Test project descriptor 'FlaggedBuilderKt' does not override 'equals()' and 'hashCode()', so its non-static instances are not reused between tests">FlaggedBuilderKt</error> : LightProjectDescriptor() {
  fun withFoo(): FlaggedBuilderKt = this
}
