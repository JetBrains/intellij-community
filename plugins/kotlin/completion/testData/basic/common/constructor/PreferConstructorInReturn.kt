class FramePrefixViewAodel
class FramePrefixViewModelA
class FramePrefixViewModel
class FramePrefixViewModelB
class FramePrefixViewPodel

fun create(): FramePrefixViewModel {
    return FramePrefix<caret>
}


// WITH_ORDER
// EXIST: {"lookupString": "FramePrefixViewModel", "tailText": " (<root>)" }
// EXIST: {"lookupString": "FramePrefixViewModel", "tailText": "() (<root>)" }
// EXIST: FramePrefixViewAodel
// EXIST: FramePrefixViewModelA
// EXIST: FramePrefixViewModelB
// EXIST: FramePrefixViewPodel
// NOTHING_ELSE
// IGNORE_K1