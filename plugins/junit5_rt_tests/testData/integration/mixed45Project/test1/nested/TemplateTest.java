package nested;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

abstract class TemplateTest
{
    @Nested
    class NestedTests extends NestedTemplate
    {
        NestedTests()
        {
            super( TemplateTest.this.getClass() );
        }

    }
}

abstract class NestedTemplate
{
    private Class<?> specificTestClass;

    NestedTemplate( Class<?> specificTestClass )
    {
        this.specificTestClass = specificTestClass;
    }

    @Test
    void myTest() {
        System.out.println( "The " + specificTestClass.getSimpleName() + " was run!" );
    }

    @Test
    void myTest1() {
        System.out.println( "Method myTest1 of " + specificTestClass.getSimpleName() );
    }

}

class FirstConcreteTest extends TemplateTest { }
class SecondConcreteTest extends TemplateTest { }
