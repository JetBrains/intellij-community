package testData.libraries

public val String.exProp : String
  get() {
    return this
  }

public val Int.exProp : Int
  get() {
    return this
  }

public val <T> java.util.List<T>.exProp : String
  get() {
    return ""
  }

context(String)
public val <T> java.util.List<T>.exPropWithContextReceiver : String
  get() {
    return ""
  }
