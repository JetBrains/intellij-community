package conditional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

class ConditionalTests {

	@Test
	@EnabledIf("conditional.ExternalCondition#customConditionTrue")
	void enabled() {}

	@Test
	@EnabledIf("conditional.ExternalCondition#customConditionFalse")
	void disabled() {}
}


