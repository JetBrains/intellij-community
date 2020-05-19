The module with utility methods for text matching features.

It is supposed to be shared with other project, including non-AWT one.

Thus the main rules of adding new method here are:

- avoid external dependencies such as JDOM, Log4j
- avoid dependencies on non-util IntelliJ-specific modules or using IntelliJ-specific classes such as Registry, Disposable, ProgressManager, etc.
- avoid using AWT or Swing classes there (Icon, KeyEvent, HTMLEditorKit, etc.)