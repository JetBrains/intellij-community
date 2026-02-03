// "Replace deprecated '@Experimental' annotation with '@RequiresOptIn'" "true"
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn -Xexperimental
// WITH_STDLIB 1.7.0

@Experimental<caret>(level=Experimental.Level.WARNING)
annotation class Marker

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.migration.MigrateExperimentalToRequiresOptInFix