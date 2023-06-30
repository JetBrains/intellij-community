// KTIJ-24099
// ALLOW_ERRORS

public class Hierarchy extends KotlinOnlyClass {
    void companionMembers() {
        Custom companionlateinitVariable = KotlinOnlyClass.companionlateinitVariable;
        Custom companionlateinitStaticVariable = KotlinOnlyClass.companionlateinitStaticVariable;

    }