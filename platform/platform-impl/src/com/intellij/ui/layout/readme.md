# Kotlin UI DSL

**Note:** This document covers the Kotlin UI DSL in IntelliJ IDEA 2019.2. A lot of the features described in this document are not available for plugins targeting earlier IntelliJ IDEA versions.

## Layout Structure

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
Every component is effectively placed in its own grid cell.

The label for the row can be specified as a parameter for the `row` method:

```kotlin
row("Parameters") { ... }
```

Rows can be nested. Components in a nested row block are considered to be subordinate to the containing row and are indented accordingly.

```kotlin
row {
  checkBox(...)
  row {
    textField(...) // indented relatively to the checkbox above
  }
}
```

To put multiple components in the same grid cell, wrap them in a `cell` method:

```kotlin
row {
  // These two components will occupy two columns in the grid
  label(...)
  textField(...)

  // These two components will be placed in the same grid cell
  cell {
    label(...)
    textField(...)
  }
}
```

To put a component on the right side of a grid row, use the `right` method:

```kotlin
row {
  rememberCheckBox()
  right {
    link("Forgot password")
  }
}
```


## Adding Components

There are two ways to add child components. The recommended way is to use factory methods `label`, `button`, `radioButton`, `hint`, `link`, etc. It allows you to create consistent UI and reuse common patterns.
  ```kotlin
  note("""Do not have an account? <a href="https://account.jetbrains.com/login">Sign Up</a>""", span, wrap)
  ```

These method also support **property bindings**, allowing you to automatically load the value displayed in the component from a property and to store it back. The easiest way to do that is to pass a reference to a Kotlin bound property:
```kotlin
checkBox("Show tabs in single row", uiSettings::scrollTabLayoutInEditor)
```

Alternatively, many factory methods support specifying a getter/setter pair for cases when a property mapping is more complicated:

```kotlin
  checkBox(
    "Show file extensions in editor tabs",
    { !uiSettings.hideKnownExtensionInTabs },
    { uiSettings.hideKnownExtensionInTabs = !it }
```

If you want to add a component for which there are no factory methods, you can simply invoke an instance of your component, using the `()` overloaded operator:
  ```kotlin
  val userField = JTextField(credentials?.userName)
  panel() {
    row { userField(grow, wrap) }
  }
  // use userField variable somehow
  ```

## Supported Components

### Labels

Use the `label` method:

```kotlin
label("Sample text")
```

### Checkboxes

See examples above.

### Radio Buttons

Radio button groups are created using the `buttonGroup` block. There are two ways to use it. If the selected radio button corresponds to a specific value of a single
property, pass the property binding to the `buttonGroup` method and the specific values to `radioButton` functions:

```kotlin
buttonGroup(mySettings::providerType) {
  row { radioButton("In native Keychain", ProviderType.KEYCHAIN) }
  row { radioButton("In KeePass", ProviderType.KEEPASS) }
}
```

If the selected radio button is controlled by multiple boolean properties, use `buttonGroup` with no binding and specify property bindings for all but one of the radio buttons:

```kotlin
buttonGroup {
  row { radioButton("The tab on the left") }
  row { radioButton("The tab on the right", uiSettings::activeRightEditorOnClose) }
  row { radioButton("Most recently opened tab", uiSettings::activeMruEditorOnClose) }
}
```

### Text Fields

Use the `textField` method for a simple text field:

```kotlin
row("Parameters:") {
    textField(settings::mergeParameters)
}
```

For entering numbers, use `intTextField`:

```kotlin
intTextField(uiSettings::editorTabLimit, columns = 4, range = EDITOR_TABS_RANGE)
```

For password text fields, there is no factory function available, so you need to use `()`:

```kotlin
val passwordField = JPasswordField()
val panel = panel {
  // ...
  row { passwordField() }
}
```

To specify the size of a text field, either pass the `columns` parameter as shown in the `intTextField` example above, or specify the `growPolicy` parameter:

```kotlin
val userField = JTextField(credentials?.userName)
val panel = panel {
    row("Username:") { userField(growPolicy = GrowPolicy.SHORT_TEXT) }
}
```

### Combo Boxes

Use the `comboBox` method with either a bound property or a getter/setter pair:

```kotlin
comboBox(DefaultComboBoxModel<Int>(tabPlacements), uiSettings::editorTabPlacement)

comboBox<PgpKey>(
  pgpListModel,
  { getSelectedPgpKey() ?: pgpListModel.items.firstOrNull() },
  { mySettings.state.pgpKeyId = if (usePgpKey.isSelected) it?.keyId else null })
```

### Spinners

Use the `spinner` method:

```kotlin
spinner(retypeOptions::retypeDelay, minValue = 0, maxValue = 5000, step = 50)
```

### Link Label

Use the `link` method:

```kotlin
link("Forgot password?") {
  BrowserUtil.browse("https://account.jetbrains.com/forgot-password")
}
```

### Separators

Use the `titledRow` method and put the controls under the separator into the nested block:

```kotlin
titledRow("Appearance") {
  row { checkBox(...) }
}
```

### Explanatory Text

Use the `comment` parameter:

```kotlin
checkBox(message("checkbox.smart.tab.reuse"),
       uiSettings::reuseNotModifiedTabs,
       comment = message("checkbox.smart.tab.reuse.inline.help"))
```

## Integrating panels with property bindings

A panel returned by the `panel` method is an instance of `DialogPanel`. This base class supports the standard `apply`, `reset`, and `isModified` methods.

If you're using this panel as the main panel of a `DialogWrapper`, the `apply` method will be automatically called when the dialog is closed with the OK action. The other methods are unused in this case.

If you're using this panel to implement a `Configurable`, use `BoundConfigurable` as the base class. In this case, the `Configurable` methods will be automatically delegated to the panel.

## Enabling and Disabling Controls

Use the `enableIf` method to bind the enabled state of a control to the values entered in other controls. The parameter of the method is a **predicate**.

```kotlin
checkBox("Show tabs in single row", uiSettings::scrollTabLayoutInEditor)
  .enableIf(myEditorTabPlacement.selectedValueIs(SwingConstants.TOP))
```

The available predicates are:
  * `selected` to check the selected state of a checkbox or radio button
  * `selectedValueIs` and `selectedValueMatches` to check the selected item in a combobox.
  
Predicates can be combined with `and` and `or` infix functions:

```kotlin
checkBox("Hide tabs if there is no space", uiSettings::hideTabsIfNeed)
  .enableIf(myEditorTabPlacement.selectedValueMatches { it != UISettings.TABS_NONE } and
              myScrollTabLayoutInEditorCheckBox.selected)
```


## Example

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