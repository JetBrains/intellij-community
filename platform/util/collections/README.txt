The module with utility methods for arrays and collections.

It is supposed to be shared with other project, including non-AWT one.

Thus the main rules of adding new method here are:

- avoid external dependencies besides collections-related (Trove, fastutil are ok; Log4j or JDOM are not ok)
- avoid dependencies on other IntelliJ-specific modules or using IntelliJ-specific classes such as Registry, Disposable, ProgressManager, etc.
- avoid using AWT or Swing classes there (Icon, KeyEvent, HTMLEditorKit, etc.)