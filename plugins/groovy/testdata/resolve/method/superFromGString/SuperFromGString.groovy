class Top {
	int overmeth() { 0 }
}

class Bottom extends Top {
	int overmeth() {
		println "${super.overm<ref>eth()}" // not resolved
	}
}