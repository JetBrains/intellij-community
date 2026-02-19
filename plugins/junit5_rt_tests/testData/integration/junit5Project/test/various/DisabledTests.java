package various;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DisabledTests {

	@Disabled("Disabled until bug #42 has been resolved")
	@Test
	void testWillBeSkipped() {
	}

	@Test
	void testWillBeExecuted() {
	}

	@Nested
	@Disabled("Disabled nested class")
	class NestedDisabled {
		@Test
		void testInNestedClassWillBeSkipped() {
		}
	}
}
