// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtTypeParameter
// OPTIONS: usages

inline fun <reified <caret>T> fff(labelText: String, a: Any): T {
  if (a is T) {}
  return T::class.java.getConstructor().newInstance()
}