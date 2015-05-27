import org.jetbrains.annotations.Contract
import org.jetbrains.annotations.NotNull

class Foo {
  
  @Contract("!null,true->!null")
  String delegationToInstance(@NotNull Foo f, boolean createIfNeeded) { f.getString(createIfNeeded) }

  @Contract("true->!null")
  String getString(boolean createIfNeeded) { createIfNeeded ? "" : null }

}
