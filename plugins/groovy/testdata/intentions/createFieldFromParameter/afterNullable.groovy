// "Create field for parameter 'name'" "true"

package codeInsight.createFieldFromParameterAction.test1;


import org.jetbrains.annotations.Nullable

public class TestBefore {

    @Nullable
    private final String myName;

    public TestBefore(@Nullable String name) {
        super();
        myName = name;
    }
}
