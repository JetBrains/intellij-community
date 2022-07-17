package one;


public class Parameter {
  void companionMembers(Proxy proxy) {
    proxy.Companion.getCompanionproperty();

    int companionfieldProperty1 = proxy.companionfieldProperty;

    proxy.getCompanionstaticProperty();

    int companionconstProperty1 = proxy.companionconstProperty;

    proxy.Companion.getCompanionvariable();
    proxy.Companion.setCompanionvariable(4);

    int companionfieldVariable1 = proxy.companionfieldVariable;
    proxy.companionfieldVariable = 4;

    proxy.getCompanionstaticVariable();
    proxy.setCompanionstaticVariable(4);

    Custom companionlateinitVariable = KotlinOnlyClass.companionlateinitVariable;
    proxy.Companion.getCompanionlateinitVariable();
    proxy.Companion.setCompanionlateinitVariable(companionlateinitVariable);

    Custom companionlateinitStaticVariable = KotlinOnlyClass.companionlateinitStaticVariable;
    proxy.getCompanionlateinitStaticVariable();
    proxy.setCompanionlateinitStaticVariable(companionlateinitStaticVariable);

    proxy.Companion.companionsimpleFunction(4);

    proxy.companionstaticFunction(4);

    proxy.Companion.companionextension("");

    proxy.companionstaticExtension("42");
  }
}
