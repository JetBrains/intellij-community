import org.junit.Test;

public class UnconstructableJUnit4TestCase2 {
	public UnconstructableJUnit4TestCase2() {
		this("two", 1);
	}

	private UnconstructableJUnit4TestCase2(String one, int two ) {
		// do nothing with the parameters
	}

	@Test
	public void testAssertion() {
	}
}