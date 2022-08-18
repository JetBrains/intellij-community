Modules directly depending on 'kotlin.base.frontend-agnostic' are considered shareable between FE10 and K2 plugins.
Such modules must not have dependencies on a specific compiler frontend.
Transitive dependencies are not counted.