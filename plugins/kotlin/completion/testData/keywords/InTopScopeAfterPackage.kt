// FIR_COMPARISON
// FIR_IDENTICAL
// COMPILER_ARGUMENTS: -XXLanguage:+SealedInterfaces -XXLanguage:+MultiPlatformProjects -XXLanguage:+DataObjects

package Test

<caret>

// EXIST:  abstract
// EXIST:  class
// EXIST:  class InTopScopeAfterPackage
// EXIST:  enum class
// EXIST:  enum class InTopScopeAfterPackage
// EXIST:  final
// EXIST:  fun
// EXIST:  import
// EXIST:  internal
// EXIST:  object
// EXIST:  object InTopScopeAfterPackage
// EXIST:  open
// EXIST:  private
// EXIST:  public
// EXIST:  interface
// EXIST:  interface InTopScopeAfterPackage
// EXIST:  val
// EXIST:  var
// EXIST:  operator
// EXIST:  infix
// EXIST:  sealed class
// EXIST:  sealed class InTopScopeAfterPackage
// EXIST:  sealed interface InTopScopeAfterPackage
// EXIST:  sealed interface
// EXIST:  data class
// EXIST:  data object
// EXIST:  { "lookupString":"data class", "itemText":"data class", "tailText":" InTopScopeAfterPackage(...)", "attributes":"bold" }
// EXIST:  {"lookupString":"data object InTopScopeAfterPackage","attributes":"bold","allLookupStrings":"data object InTopScopeAfterPackage","itemText":"data object InTopScopeAfterPackage"}
// EXIST:  inline
// EXIST:  value
// EXIST:  tailrec
// EXIST:  external
// EXIST:  annotation class
// EXIST:  annotation class InTopScopeAfterPackage
// EXIST:  const val
// EXIST:  suspend fun
// EXIST:  typealias
// EXIST:  expect
// EXIST:  actual
// EXIST:  lateinit var
// NOTHING_ELSE
