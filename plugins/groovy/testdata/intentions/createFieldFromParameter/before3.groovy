// "Create field for parameter 'length'" "true"

package codeInsight.createFieldFromParameterAction.test1;


public class TestBefore {
    private String myName;
    private final HashMap myTest;
    private String myPerson;

    public TestBefore(String name, int length<caret>, HashMap test, String person) {
        super();
        myName = name;
        myTest = test;
        myPerson = person;
    }
}
