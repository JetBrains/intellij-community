The module with base utility methods.

It is supposed to be shared with other project, including non-AWT one.

Thus, the main rules of adding new method here are:

- avoid external dependencies besides collections-related (fastutil are ok; Log4j or JDOM are not ok)
- avoid dependencies on other IntelliJ-specific modules or using IntelliJ-specific classes such as Key, Registry, Disposable, ProgressManager, etc.
- avoid using AWT or Swing classes there (Icon, KeyEvent, HTMLEditorKit, etc.)