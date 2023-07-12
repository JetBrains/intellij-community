// KTIJ-24099
// ALLOW_ERRORS
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY

public class Hierarchy extends KotlinOnlyClass {
    void companionMembers() {
        Custom companionlateinitVariable = KotlinOnlyClass.companionlateinitVariable;
        Custom companionlateinitStaticVariable = KotlinOnlyClass.companionlateinitStaticVariable;

    }