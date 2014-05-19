package test.misc;

import java.io.Serializable;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

public class GenericTest<T extends Object & Serializable> {

	@test.misc.ann.RequestForEnhancement(
			id       = 2868724,
		    synopsis = "Enable time-travel",
		    engineer = "Mr. Peabody",
		    date     = "4/1/3007",
		    arr      = {"1","2","3"},
		    cl       = Void.class
	)
	@Deprecated
	public boolean test(@Deprecated Collection c) {
		return true;
	}
	
	public int testparam(boolean t, @Deprecated List lst, double d) {
		return 0;
	}

}

class GenericTestChild<E extends Collection> extends GenericTest<GenericTestChild<AbstractCollection>> implements Serializable {

//	public <T> void test(Collection<T> c) {
//		T var1 = c.iterator().next();
//		c.add(var1);
//	}	
	
	public List<String>[][] field;
	
	public <T extends Date & List> void test(List<? super ArrayList> list1, List<?> list) {
		
//	l2: {
//		l1: {
//			if(Math.random() > 2){
//				break l1;
//			}
//	
//			System.out.println("1");
//			break l2;
//		}
//	
//		System.out.println("2");
//	}
	
	if(Math.random() > 2){
		System.out.println("2");
	} else {
		System.out.println("1");
	}

	
	}
	
}

