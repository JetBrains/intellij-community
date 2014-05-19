package test;

import java.util.LinkedList;
import java.util.List;

public class BradTest {

	public void testForLoop() {

		for(int i = 0; i < 5; i++) {
			System.out.println(i);
		}

		int[] testArray = new int[] {};

		for(int i : testArray) {
			System.out.println(i);
		}

		int k = 0;
		int j = 0;

		while(j < 5) {

			System.out.println("Test while.");

			j++;

			if(j < 3) {
				continue;
			}

			k++;

		}

		List keys = new java.util.ArrayList();

		for(Object test : keys) {

			System.out.println(test);

			System.out.println(test);

			System.out.println(test);

		}

		List<BradTest> objects = new LinkedList<BradTest>();

		for(BradTest test : objects) {
			System.out.println(test);

		}
	}

}
