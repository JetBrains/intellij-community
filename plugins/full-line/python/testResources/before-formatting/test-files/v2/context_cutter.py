import abc
from typing import List, Type, Optional

from data_processing.datapoint import CodeDataPoint
from data_processing.processors.abstract_processor import ProcessorBase
from data_processing.processors.yttm_tokenizer import YttmTokenizerBase


class ContextCutterBase(ProcessorBase):
    def __init__(self, context_length: int, reserved_length: int, overlap_ratio: float):
        self._context_length = context_length
        self._reserved_length = reserved_length
        self._overlap_ratio = overlap_ratio
        assert 0 <= self._overlap_ratio < 1
        assert self._reserved_length < self._context_length

    @abc.abstractmethod
    def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:
        raise NotImplementedError

    @staticmethod
    def is_deterministic() -> bool:
        return True

    @staticmethod
    def is_slow() -> bool:
        return False

    @property
    def context_length(self) -> int:
        return self._context_length

    @staticmethod
    def must_be_after() -> List[Type[ProcessorBase]]:
        return [YttmTokenizerBase]

    @staticmethod
    def must_be_before() -> List[Type[ProcessorBase]]:
        return []


class ContextCutterTrainData(ContextCutterBase):
    def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:
        dp.composed_tokens = [dp.metainfo_tokens + dp.tokens]
        return dp


class ContextCutterTrainModel(ContextCutterBase):
    def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:
        desired_length = self._context_length - len(dp.metainfo_tokens)
        dp.overlap_len = int(self._overlap_ratio * desired_length)

        dp.composed_tokens = []
        for start in range(0, len(dp.tokens), desired_length - dp.overlap_len):
            tokens = dp.metainfo_tokens + dp.tokens[start : start + desired_length]
            dp.composed_tokens.append(tokens)

        return dp


class ContextCutterInference(ContextCutterBase):
    def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:
        desired_length = self._context_length - len(dp.metainfo_tokens) - self._reserved_length
        tokens = dp.metainfo_tokens + dp.tokens[-desired_length:]
        dp.composed_tokens = [tokens]
        return dp
