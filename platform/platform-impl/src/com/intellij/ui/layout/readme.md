# UI DSL

Use `panel` to create UI:

```kotlin
panel {
  row {
    // child components
  }
}
```

Rows are created vertically from top to bottom, in the same order as lines of code that call `row`.
Inside one row, you add components from left to right in the same order calls to factory method or `()` appear in each row.

There are two ways to add child components:
* Using factory methods `label`, `button`, `radioButton`, `hint`, `link`, etc. It allows you to create consistent UI and reuse common patterns.
  ```kotlin
  note("""Do not have an account? <a href="https://account.jetbrains.com/login">Sign Up</a>""", span, wrap)
  ```
* Invoking instance of your component — `()`.
  ```kotlin
  val userField = JTextField(credentials?.userName)
  panel() {
    row { userField(grow, wrap) }
  }
  // use userField variable somehow
  ```
  
  Or, if you don't need to reference component, you can of course create component in-place.
  ```kotlin
  JTextField(credentials?.userName)(grow, wrap)
  ```
  
Example:
```kotlin
val panel = panel {
  noteRow("Login to get notified when the submitted\nexceptions are fixed.")
  row("Username:") { userField() }
  row("Password:") { passwordField() }
  row {
    rememberCheckBox()
    right {
      link("Forgot password?") { /* custom action */ }
    }
  }
  noteRow("""Do not have an account? <a href="https://account.jetbrains.com/login">Sign Up</a>""")
}
```

## FAQ

### One cell is minimum, second one is maximum

Set `CCFlags.growX` and `CCFlags.pushX` for some component in the second cell.