public class App {
  void f(org.slf4j.Logger log) {
    log.warn("message", /*1*/new /*2*/ <caret>Object/*3*/[/*4*/]/*5*/{/*6*//*end*/});
    log.warn("message", (/*1*/new /*2*/ Object/*3*/[/*4*/]/*5*/{/*6*//*end*/}));
    log.warn("{}", /*1*/new /*2*/ Object/*3*/[/*4*/]/*5*/{/*6*/1/*end*/});
    log.warn("{}", (/*1*/new /*2*/ Object/*3*/[/*4*/]/*5*/{/*6*/1/*end*/}));
    log.warn("{}", ((/*1*/new /*2*/ Object/*3*/[/*4*/]/*5*/{/*6*/1/*end*/})));
    log.warn("{}, {}!", /*1*/new /*2*/ Object/*3*/[/*4*/]/*5*/{/*6*/"Hello"/*7*/, /*8*/ "World"/*end*/});
    log.warn("{}, {}!", (/*1*/new /*2*/ Object/*3*/[/*4*/]/*5*/{/*6*/"Hello"/*7*/, /*8*/ "World"/*end*/}));
    log.warn("{}, {}!", ((/*1*/new /*2*/ Object/*3*/[/*4*/]/*5*/{/*6*/"Hello"/*7*/, /*8*/ "World"/*end*/})));
    log.warn("{}, {} {}!", (/*1*/new /*2*/ Object/*3*/[/*4*/]/*5*/{/*6*/"Hello"/*7*/, /*8*/ "World", "World"/*end*/}));
  }
}