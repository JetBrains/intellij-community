package various;

import org.junit.jupiter.api.Test;

class UncaughtExceptionHandlingDemo {
	@Test
	void failsDueToUncaughtException() {
		throw new RuntimeException("Boom!");
	}

	@Test
	void passes() {}
}
