This package is copied from `fleet.fastutil` module.
It's tricky to make `fleet.fastutil` module compile with Java8 which
is the requirement for `intellij.platform.syntax.impl`.
This is a temporary solution until the compatibility with `fleet.fastutil` id not resolved.
Thus, please don't make the declarations in this package public. 