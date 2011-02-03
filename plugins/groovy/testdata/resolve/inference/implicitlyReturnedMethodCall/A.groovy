Map<BasicRange, Map<BasicRange, Double>> getBasic() {
    Map<BasicRange, List<String>> ranges = new HashMap<BasicRange, List<String>>()
    ranges.keySet().collectEntri<ref>es {
        [it, getBasic(it)]
    }
}

Map<BasicRange, Double> getBasic(BasicRange range) {return new HashMap<BasicRange, Double>()}

class BasicRange {}


