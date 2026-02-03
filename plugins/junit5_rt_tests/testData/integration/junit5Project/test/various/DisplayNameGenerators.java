package various;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.DisplayNameGenerator.IndicativeSentences.SentenceFragment;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DisplayNameGenerators {

    @Nested
    @DisplayNameGeneration(ReplaceUnderscores.class)
    class Replace_underscores_in_class {

        @Test
        void replace_underscores_in_method() {}

        @Test
        @DisplayName("Display_name_with_underscores")
        void test_with_display_name() {}

        @ParameterizedTest(name = "Parameterized test name works {0}")
        @ValueSource(ints = {1, 2})
        void parameterized_test(int val) {
        }
    }

    @Nested
    @SentenceFragment("Fragment1")
    @IndicativeSentencesGeneration
    class SentenceFragmentTests {

        @SentenceFragment("fragment2")
        @Test
        void fragmentTest() {}

        @SentenceFragment("fragment3")
        @ParameterizedTest(name = "{0}")
        @ValueSource(ints = {1, 2, 3})
        void parameterizedFragmentTest(int year) {}
    }
}
