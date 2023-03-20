from typing import (
    List,
)

version: int

class mpatchError(Exception): ...

def patches(text: bytes, bins: List[bytes]) -> bytes: ...
def patchedsize(orig: int, data: bytes) -> int: ...
