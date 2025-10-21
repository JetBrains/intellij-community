package various;

import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.*;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

class TestTemplateDemo {

    @TestTemplate
    @ExtendWith(ContextProvider.class)
    void testTemplateMethod(String text) {}

    static public class ContextProvider implements TestTemplateInvocationContextProvider {

        @Override
        public boolean supportsTestTemplate(ExtensionContext context) { return true; }

        @Override
        public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
            return Stream.of(invocationContext("first test "), invocationContext("second test "));
        }

        private TestTemplateInvocationContext invocationContext(String parameter) {
            return new TestTemplateInvocationContext() {
                @Override
                public String getDisplayName(int i) {return parameter + i;}

                @Override
                public List<Extension> getAdditionalExtensions() {
                    return Collections.singletonList(new ParameterResolver() {
                        @Override
                        public boolean supportsParameter(ParameterContext p, ExtensionContext e) { return true; }

                        @Override
                        public Object resolveParameter(ParameterContext p, ExtensionContext e) { return parameter;}
                    });
                }
            };
        }
    }

}
