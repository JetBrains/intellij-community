// "Create field for parameter 'test'" "true"

package codeInsight.createFieldFromParameterAction.test1;


public class TestBefore {
    private String myName;
    private String myPerson;

    public TestBefore(String name, int length, HashMap test<caret>, String person) {
        super();
        myName = name;
        myPerson = person;
    }
}
