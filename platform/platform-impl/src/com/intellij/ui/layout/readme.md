Use `panel` to create UI:

```kotlin
panel(fillX) {
  // child components
}
```
There are two ways to add child components:
* Using factory methods `label`, `button`, `radioButton`, `hint`, `link`, `note`, `panel`, etc. It allows you to create consistent UI and reuse common patterns (for example, `note` automatically adds required top gap).
  ```kotlin
  note("""Do not have an account? <a href="https://account.jetbrains.com/login">Sign Up</a>""", span, wrap)
  ```
* Invoking instance of your component — `()`.
  ```kotlin
  val userField = JTextField(credentials?.userName)
  panel() {
    userField(grow, wrap)
  }
  // use userField variable somehow
  ```
  
  Or, if you don't need to reference component, you can of course create component in-place.
  ```kotlin
  JTextField(credentials?.userName)(grow, wrap)
  ```
   
Example:
```kotlin
val panel = panel(fillX) {
  label("Login to get notified when the submitted\nexceptions are fixed.", span, wrap)
  label("Username:")
  userField(grow, wrap)

  label("Password:")
  passwordField(grow, wrap)
  rememberCheckBox(skip, split, grow)
  link("Forgot password?", wrap, right) {
    // custom action
  }

  note("""Do not have an account? <a href="https://example.com/login">Sign Up</a>""", span, wrap)
}
```