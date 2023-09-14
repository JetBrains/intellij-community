package test

@Deprecated("hidden", level = DeprecationLevel.HIDDEN)
class DeprecatedHiddenClass

@Deprecated("error", level = DeprecationLevel.ERROR)
class DeprecatedErrorNotHiddenClass

class NotHiddenClass

// ALLOW_AST_ACCESS